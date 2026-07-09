import { useTranslation } from 'react-i18next'
import SupplierRegisterPage from './SupplierRegisterPage'

/**
 * Public registration is for SUPPLIERS ONLY.
 * All staff accounts (AA, validators, DAF, Admin) are created exclusively
 * by the Administrator via the User Management console.
 * There is no public self-registration for internal roles.
 */
export default function RegisterPage() {
  const { t } = useTranslation()

  return (
    <div className="min-h-screen bg-ground">
      <div className="max-w-3xl mx-auto pt-8 px-4 pb-2">
        <div className="bg-info-bg border border-info/30 rounded-[4px] px-4 py-3 text-sm text-info">
          <p className="font-semibold">{t('register.supplierOnly.title', 'Supplier portal registration')}</p>
          <p className="mt-0.5 text-info">
            {t('register.supplierOnly.note', 'This form is for external suppliers only. OCT staff accounts are created by the System Administrator — contact your IT department for access.')}
          </p>
        </div>
      </div>
      <SupplierRegisterPage />
    </div>
  )
}
